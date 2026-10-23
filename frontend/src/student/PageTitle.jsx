/** Heading for every page opened from the "Student Subscription and Payment" dropdown: the module name, then the page name beside it. */
export default function PageTitle({ name }) {
  return (
    <h1 className="page-title">
      Student Subscription and Payment <span className="page-title-sep" aria-hidden="true">›</span> <span className="page-title-name">{name}</span>
    </h1>
  );
}
